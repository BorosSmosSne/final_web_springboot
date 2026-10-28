package com.setec.repos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.setec.entities.Product;

@Repository
public interface ProductRepo extends JpaRepository<Product, Integer> {

	List<Product> findByName(String name);
	
	// added: Find all products belonging to a a category
	List<Product> findByCategoryId(Integer categoryId);


	
}
