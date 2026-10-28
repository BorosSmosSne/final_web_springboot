package com.setec.repos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.Category;

public interface CategoryRepos extends JpaRepository<Category, Integer>{

}
